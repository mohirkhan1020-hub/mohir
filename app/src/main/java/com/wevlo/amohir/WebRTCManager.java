package com.wevlo.amohir;

import android.content.Context;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.MediaStreamTrack;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.RtpTransceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WebRTCManager {

    public interface Callback {
        void onIceCandidateGenerated(IceCandidate candidate);
        void onRemoteStreamAdded(VideoTrack videoTrack);
        void onConnectionStateChanged(PeerConnection.PeerConnectionState state);
    }

    private final Context context;
    private final EglBase eglBase;
    private final PeerConnectionFactory factory;
    private final Callback callback;

    private PeerConnection peerConnection;
    private AudioSource audioSource;
    private AudioTrack localAudioTrack;
    private VideoSource videoSource;
    private VideoTrack localVideoTrack;
    private CameraVideoCapturer videoCapturer;
    private SurfaceTextureHelper surfaceTextureHelper;

    public WebRTCManager(Context context, EglBase eglBase, Callback callback) {
        this.context = context.getApplicationContext();
        this.eglBase = eglBase;
        this.callback = callback;

        PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(this.context)
                        .createInitializationOptions()
        );

        factory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(new DefaultVideoEncoderFactory(eglBase.getEglBaseContext(), true, true))
                .setVideoDecoderFactory(new DefaultVideoDecoderFactory(eglBase.getEglBaseContext()))
                .createPeerConnectionFactory();
    }

    public void startLocalMedia(boolean isVideo, SurfaceViewRenderer localView) {
        MediaConstraints audioConstraints = new MediaConstraints();
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googEchoCancellation", "true"));
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googNoiseSuppression", "true"));
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googAutoGainControl", "true"));

        audioSource = factory.createAudioSource(audioConstraints);
        localAudioTrack = factory.createAudioTrack("ARDAMSa0", audioSource);
        localAudioTrack.setEnabled(true);

        if (isVideo) {
            videoCapturer = createCameraCapturer();
            if (videoCapturer != null) {
                surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.getEglBaseContext());
                videoSource = factory.createVideoSource(videoCapturer.isScreencast());
                videoCapturer.initialize(surfaceTextureHelper, context, videoSource.getCapturerObserver());
                videoCapturer.startCapture(1280, 720, 30);

                localVideoTrack = factory.createVideoTrack("ARDAMSv0", videoSource);
                localVideoTrack.setEnabled(true);
                localVideoTrack.addSink(localView);
            }
        }
    }

    private CameraVideoCapturer createCameraCapturer() {
        Camera2Enumerator enumerator = new Camera2Enumerator(context);
        for (String name : enumerator.getDeviceNames()) {
            if (enumerator.isFrontFacing(name)) {
                CameraVideoCapturer capturer = enumerator.createCapturer(name, null);
                if (capturer != null) return capturer;
            }
        }
        for (String name : enumerator.getDeviceNames()) {
            if (!enumerator.isFrontFacing(name)) {
                CameraVideoCapturer capturer = enumerator.createCapturer(name, null);
                if (capturer != null) return capturer;
            }
        }
        return null;
    }

    public void createPeerConnection() {
        List<PeerConnection.IceServer> iceServers = new ArrayList<>();
        iceServers.add(PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer());
        iceServers.add(PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer());

        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;

        peerConnection = factory.createPeerConnection(rtcConfig, new PeerConnection.Observer() {
            @Override
            public void onIceCandidate(IceCandidate candidate) {
                callback.onIceCandidateGenerated(candidate);
            }

            @Override
            public void onTrack(RtpTransceiver transceiver) {
                MediaStreamTrack track = transceiver.getReceiver().track();
                if (track instanceof VideoTrack) {
                    callback.onRemoteStreamAdded((VideoTrack) track);
                }
            }

            @Override
            public void onConnectionChange(PeerConnection.PeerConnectionState newState) {
                callback.onConnectionStateChanged(newState);
            }

            @Override public void onSignalingChange(PeerConnection.SignalingState s) {}
            @Override public void onIceConnectionChange(PeerConnection.IceConnectionState s) {}
            @Override public void onIceConnectionReceivingChange(boolean b) {}
            @Override public void onIceGatheringChange(PeerConnection.IceGatheringState s) {}
            @Override public void onIceCandidatesRemoved(IceCandidate[] c) {}
            @Override public void onAddStream(MediaStream s) {}
            @Override public void onRemoveStream(MediaStream s) {}
            @Override public void onDataChannel(DataChannel dc) {}
            @Override public void onRenegotiationNeeded() {}
            @Override public void onAddTrack(RtpReceiver r, MediaStream[] s) {}
        });

        if (peerConnection != null) {
            List<String> streamIds = Collections.singletonList("ARDAMS");
            if (localAudioTrack != null) peerConnection.addTrack(localAudioTrack, streamIds);
            if (localVideoTrack != null) peerConnection.addTrack(localVideoTrack, streamIds);
        }
    }

    public void createOffer(SdpResultListener listener) {
        if (peerConnection == null) return;
        MediaConstraints constraints = new MediaConstraints();
        peerConnection.createOffer(new SimpleSdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sdp) {
                peerConnection.setLocalDescription(new SimpleSdpObserver(), sdp);
                listener.onSuccess(sdp);
            }
        }, constraints);
    }

    public void createAnswer(SdpResultListener listener) {
        if (peerConnection == null) return;
        MediaConstraints constraints = new MediaConstraints();
        peerConnection.createAnswer(new SimpleSdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sdp) {
                peerConnection.setLocalDescription(new SimpleSdpObserver(), sdp);
                listener.onSuccess(sdp);
            }
        }, constraints);
    }

    public void setRemoteDescription(String type, String sdp) {
        if (peerConnection == null) return;
        SessionDescription.Type sdpType = SessionDescription.Type.fromCanonicalForm(type.toLowerCase());
        peerConnection.setRemoteDescription(new SimpleSdpObserver(), new SessionDescription(sdpType, sdp));
    }

    public void addIceCandidate(String sdpMid, int sdpMLineIndex, String candidate) {
        if (peerConnection != null) {
            peerConnection.addIceCandidate(new IceCandidate(sdpMid, sdpMLineIndex, candidate));
        }
    }

    public void setMicEnabled(boolean enabled) {
        if (localAudioTrack != null) localAudioTrack.setEnabled(enabled);
    }

    public void setCamEnabled(boolean enabled) {
        if (localVideoTrack != null) localVideoTrack.setEnabled(enabled);
    }

    public void switchCamera() {
        if (videoCapturer != null) {
            videoCapturer.switchCamera(null);
        }
    }

    public void endCall() {
        if (videoCapturer != null) {
            try { videoCapturer.stopCapture(); } catch (InterruptedException ignored) {}
            videoCapturer.dispose();
            videoCapturer = null;
        }
        if (surfaceTextureHelper != null) {
            surfaceTextureHelper.dispose();
            surfaceTextureHelper = null;
        }
        if (videoSource != null) {
            videoSource.dispose();
            videoSource = null;
        }
        if (audioSource != null) {
            audioSource.dispose();
            audioSource = null;
        }
        if (peerConnection != null) {
            peerConnection.close();
            peerConnection = null;
        }
        localAudioTrack = null;
        localVideoTrack = null;
    }

    public void destroyFactory() {
        endCall();
        factory.dispose();
    }

    public interface SdpResultListener {
        void onSuccess(SessionDescription sdp);
    }

    private static class SimpleSdpObserver implements SdpObserver {
        @Override public void onCreateSuccess(SessionDescription sdp) {}
        @Override public void onSetSuccess() {}
        @Override public void onCreateFailure(String s) {}
        @Override public void onSetFailure(String s) {}
    }
}
