package com.wevlo.amohir;

import android.content.Context;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
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
import org.webrtc.audio.AudioDeviceModule;
import org.webrtc.audio.JavaAudioDeviceModule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WebRTCManager {

    private static final String TURN_USERNAME = "17b22c937180a3b673ac074d";
    private static final String TURN_PASSWORD = "fxt4t0cdkpDmhVzw";

    public interface Callback {
        void onIceCandidateGenerated(IceCandidate candidate);
        void onRemoteStreamAdded(VideoTrack videoTrack);
        void onConnectionStateChanged(PeerConnection.PeerConnectionState state);
        void onError(String message);
    }

    public interface SdpResultListener {
        void onSuccess(SessionDescription sdp);
    }

    private final Context context;
    private final EglBase eglBase;
    private final Callback callback;
    private final AudioDeviceModule audioDeviceModule;
    private final PeerConnectionFactory factory;

    private PeerConnection peerConnection;
    private AudioSource audioSource;
    private AudioTrack localAudioTrack;
    private VideoSource videoSource;
    private VideoTrack localVideoTrack;
    private CameraVideoCapturer videoCapturer;
    private SurfaceTextureHelper surfaceTextureHelper;
    private SurfaceViewRenderer localViewRef;

    private boolean isVideoCall = true;
    private boolean isRemoteSdpSet = false;
    private final List<IceCandidate> pendingIceCandidates = new ArrayList<>();
    private final Object iceLock = new Object();

    public WebRTCManager(Context context, EglBase eglBase, Callback callback) {
        this.context = context.getApplicationContext();
        this.eglBase = eglBase;
        this.callback = callback;

        PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(this.context).createInitializationOptions()
        );

        audioDeviceModule = JavaAudioDeviceModule.builder(this.context)
                .setUseHardwareAcousticEchoCanceler(true)
                .setUseHardwareNoiseSuppressor(true)
                .createAudioDeviceModule();

        factory = PeerConnectionFactory.builder()
                .setAudioDeviceModule(audioDeviceModule)
                .setVideoEncoderFactory(new DefaultVideoEncoderFactory(eglBase.getEglBaseContext(), true, true))
                .setVideoDecoderFactory(new DefaultVideoDecoderFactory(eglBase.getEglBaseContext()))
                .createPeerConnectionFactory();
    }

    public void startLocalMedia(boolean isVideo, SurfaceViewRenderer localView) {
        this.isVideoCall = isVideo;
        this.localViewRef = localView;

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
                try {
                    videoCapturer.startCapture(1280, 720, 30);
                } catch (Exception e) {
                    callback.onError("Camera error: " + e.getMessage());
                }

                localView.setMirror(true);
                localVideoTrack = factory.createVideoTrack("ARDAMSv0", videoSource);
                localVideoTrack.setEnabled(true);
                localVideoTrack.addSink(localView);
            } else {
                callback.onError("No camera found");
            }
        }
    }

    private CameraVideoCapturer createCameraCapturer() {
        CameraEnumerator enumerator = Camera2Enumerator.isSupported(context)
                ? new Camera2Enumerator(context) : new Camera1Enumerator(true);
        for (String name : enumerator.getDeviceNames()) {
            if (enumerator.isFrontFacing(name)) {
                CameraVideoCapturer c = enumerator.createCapturer(name, null);
                if (c != null) return c;
            }
        }
        for (String name : enumerator.getDeviceNames()) {
            if (!enumerator.isFrontFacing(name)) {
                CameraVideoCapturer c = enumerator.createCapturer(name, null);
                if (c != null) return c;
            }
        }
        return null;
    }

    public void createPeerConnection() {
        synchronized (iceLock) {
            isRemoteSdpSet = false;
            pendingIceCandidates.clear();
        }

        List<PeerConnection.IceServer> iceServers = new ArrayList<>();
        iceServers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());
        iceServers.add(PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer());
        iceServers.add(PeerConnection.IceServer.builder("stun:stun.relay.metered.ca:80").createIceServer());

        iceServers.add(PeerConnection.IceServer.builder("turn:standard.relay.metered.ca:80")
                .setUsername(TURN_USERNAME).setPassword(TURN_PASSWORD).createIceServer());
        iceServers.add(PeerConnection.IceServer.builder("turn:standard.relay.metered.ca:80?transport=tcp")
                .setUsername(TURN_USERNAME).setPassword(TURN_PASSWORD).createIceServer());
        iceServers.add(PeerConnection.IceServer.builder("turn:standard.relay.metered.ca:443")
                .setUsername(TURN_USERNAME).setPassword(TURN_PASSWORD).createIceServer());
        iceServers.add(PeerConnection.IceServer.builder("turns:standard.relay.metered.ca:443?transport=tcp")
                .setUsername(TURN_USERNAME).setPassword(TURN_PASSWORD).createIceServer());
        iceServers.add(PeerConnection.IceServer.builder("turn:global.relay.metered.ca:80")
                .setUsername(TURN_USERNAME).setPassword(TURN_PASSWORD).createIceServer());
        iceServers.add(PeerConnection.IceServer.builder("turn:global.relay.metered.ca:443")
                .setUsername(TURN_USERNAME).setPassword(TURN_PASSWORD).createIceServer());

        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;

        peerConnection = factory.createPeerConnection(rtcConfig, new PeerConnection.Observer() {
            @Override public void onIceCandidate(IceCandidate candidate) { callback.onIceCandidateGenerated(candidate); }

            @Override
            public void onTrack(RtpTransceiver transceiver) {
                MediaStreamTrack track = transceiver.getReceiver().track();
                if (track instanceof VideoTrack) callback.onRemoteStreamAdded((VideoTrack) track);
            }

            @Override
            public void onIceConnectionChange(PeerConnection.IceConnectionState newState) {
                if (newState == PeerConnection.IceConnectionState.CONNECTED || newState == PeerConnection.IceConnectionState.COMPLETED) {
                    callback.onConnectionStateChanged(PeerConnection.PeerConnectionState.CONNECTED);
                } else if (newState == PeerConnection.IceConnectionState.FAILED) {
                    callback.onConnectionStateChanged(PeerConnection.PeerConnectionState.FAILED);
                }
            }

            @Override public void onConnectionChange(PeerConnection.PeerConnectionState newState) { callback.onConnectionStateChanged(newState); }
            @Override public void onSignalingChange(PeerConnection.SignalingState s) {}
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
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", String.valueOf(isVideoCall)));
        peerConnection.createOffer(new SimpleSdpObserver() {
            @Override public void onCreateSuccess(SessionDescription sdp) {
                peerConnection.setLocalDescription(new SimpleSdpObserver() {
                    @Override public void onSetSuccess() { listener.onSuccess(sdp); }
                    @Override public void onSetFailure(String err) { callback.onError("Local SDP error: " + err); }
                }, sdp);
            }
            @Override public void onCreateFailure(String err) { callback.onError("Offer error: " + err); }
        }, constraints);
    }

    public void createAnswer(SdpResultListener listener) {
        if (peerConnection == null) return;
        MediaConstraints constraints = new MediaConstraints();
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", String.valueOf(isVideoCall)));
        peerConnection.createAnswer(new SimpleSdpObserver() {
            @Override public void onCreateSuccess(SessionDescription sdp) {
                peerConnection.setLocalDescription(new SimpleSdpObserver() {
                    @Override public void onSetSuccess() { listener.onSuccess(sdp); }
                    @Override public void onSetFailure(String err) { callback.onError("Local SDP error: " + err); }
                }, sdp);
            }
            @Override public void onCreateFailure(String err) { callback.onError("Answer error: " + err); }
        }, constraints);
    }

    public void setRemoteDescription(String type, String sdp, Runnable onSuccess) {
        if (peerConnection == null || type == null || sdp == null) return;
        SessionDescription.Type sdpType = SessionDescription.Type.fromCanonicalForm(type.toLowerCase());
        peerConnection.setRemoteDescription(new SimpleSdpObserver() {
            @Override public void onSetSuccess() {
                synchronized (iceLock) {
                    isRemoteSdpSet = true;
                    for (IceCandidate c : pendingIceCandidates) peerConnection.addIceCandidate(c);
                    pendingIceCandidates.clear();
                }
                if (onSuccess != null) onSuccess.run();
            }
            @Override public void onSetFailure(String err) { callback.onError("Remote SDP error: " + err); }
        }, new SessionDescription(sdpType, sdp));
    }

    public void addIceCandidate(String sdpMid, int sdpMLineIndex, String candidate) {
        if (peerConnection == null || candidate == null) return;
        IceCandidate ice = new IceCandidate(sdpMid, sdpMLineIndex, candidate);
        synchronized (iceLock) {
            if (isRemoteSdpSet) peerConnection.addIceCandidate(ice);
            else pendingIceCandidates.add(ice);
        }
    }

    public void setMicEnabled(boolean enabled) { if (localAudioTrack != null) localAudioTrack.setEnabled(enabled); }
    public void setCamEnabled(boolean enabled) { if (localVideoTrack != null) localVideoTrack.setEnabled(enabled); }

    public void switchCamera() {
        if (videoCapturer != null) {
            videoCapturer.switchCamera(new CameraVideoCapturer.CameraSwitchHandler() {
                @Override
                public void onCameraSwitchDone(boolean isFrontCamera) {
                    if (localViewRef != null) {
                        localViewRef.post(() -> localViewRef.setMirror(isFrontCamera));
                    }
                }
                @Override public void onCameraSwitchError(String s) {}
            });
        }
    }

    public void endCall() {
        synchronized (iceLock) { isRemoteSdpSet = false; pendingIceCandidates.clear(); }
        if (videoCapturer != null) {
            try { videoCapturer.stopCapture(); } catch (InterruptedException ignored) {}
            videoCapturer.dispose(); videoCapturer = null;
        }
        if (surfaceTextureHelper != null) { surfaceTextureHelper.dispose(); surfaceTextureHelper = null; }
        if (videoSource != null) { videoSource.dispose(); videoSource = null; }
        if (audioSource != null) { audioSource.dispose(); audioSource = null; }
        if (peerConnection != null) { peerConnection.close(); peerConnection = null; }
        localAudioTrack = null; localVideoTrack = null; localViewRef = null;
    }

    public void destroyFactory() {
        endCall();
        factory.dispose();
        if (audioDeviceModule != null) audioDeviceModule.release();
    }

    private static class SimpleSdpObserver implements SdpObserver {
        @Override public void onCreateSuccess(SessionDescription sdp) {}
        @Override public void onSetSuccess() {}
        @Override public void onCreateFailure(String s) {}
        @Override public void onSetFailure(String s) {}
    }
}