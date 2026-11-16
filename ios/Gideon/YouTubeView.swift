import SwiftUI
import WebKit

/// YouTube's embedded player (IFrame API), chromeless in the feed; Gideon drives play, pause, mute and replay.
struct YouTubeView: UIViewRepresentable {
    enum Event {
        case ready, playing, paused, ended, failed
    }

    static let origin = URL(string: "https://app.gideon.ios")!

    let videoID: String
    let muted: Bool
    let playing: Bool
    let interactive: Bool
    let replayToken: Int
    let onEvent: (Event) -> Void

    init(videoID: String, muted: Bool, playing: Bool, interactive: Bool = false, replayToken: Int = 0,
         onEvent: @escaping (Event) -> Void = { _ in }) {
        self.videoID = videoID
        self.muted = muted
        self.playing = playing
        self.interactive = interactive
        self.replayToken = replayToken
        self.onEvent = onEvent
    }

    func makeCoordinator() -> Coordinator { Coordinator() }

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.allowsInlineMediaPlayback = true
        config.allowsPictureInPictureMediaPlayback = false
        config.mediaTypesRequiringUserActionForPlayback = []
        config.userContentController.add(context.coordinator, name: "gideon")
        let web = WKWebView(frame: .zero, configuration: config)
        web.isOpaque = false
        web.backgroundColor = .black
        web.scrollView.backgroundColor = .black
        web.scrollView.isScrollEnabled = false
        web.scrollView.contentInsetAdjustmentBehavior = .never
        web.isUserInteractionEnabled = interactive
        let coordinator = context.coordinator
        coordinator.onEvent = onEvent
        coordinator.muted = muted
        coordinator.playing = playing
        coordinator.replayToken = replayToken
        web.loadHTMLString(Self.page(videoID: videoID, muted: muted, autoplay: playing, controls: interactive),
                           baseURL: Self.origin)
        return web
    }

    func updateUIView(_ web: WKWebView, context: Context) {
        let coordinator = context.coordinator
        coordinator.onEvent = onEvent
        if coordinator.muted != muted {
            coordinator.muted = muted
            web.evaluateJavaScript(muted ? "gideon('mute')" : "gideon('unMute')", completionHandler: nil)
        }
        if coordinator.playing != playing {
            coordinator.playing = playing
            web.evaluateJavaScript(playing ? "gideon('playVideo')" : "gideon('pauseVideo')", completionHandler: nil)
        }
        if coordinator.replayToken != replayToken {
            coordinator.replayToken = replayToken
            web.evaluateJavaScript("gideon('replay')", completionHandler: nil)
        }
    }

    static func dismantleUIView(_ web: WKWebView, coordinator: Coordinator) {
        web.configuration.userContentController.removeScriptMessageHandler(forName: "gideon")
        web.stopLoading()
        web.loadHTMLString("", baseURL: nil)
    }

    final class Coordinator: NSObject, WKScriptMessageHandler {
        var onEvent: (Event) -> Void = { _ in }
        var muted = false
        var playing = false
        var replayToken = 0

        func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
            guard let name = message.body as? String else { return }
            let event: Event?
            switch name {
            case "ready": event = .ready
            case "playing": event = .playing
            case "paused": event = .paused
            case "ended": event = .ended
            case "failed": event = .failed
            default: event = nil
            }
            if let event { onEvent(event) }
        }
    }

    private static func page(videoID: String, muted: Bool, autoplay: Bool, controls: Bool) -> String {
        let safeID = videoID.filter { $0.isLetter || $0.isNumber || $0 == "-" || $0 == "_" }
        return """
        <!DOCTYPE html><html><head>
        <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
        <style>html,body{margin:0;padding:0;width:100%;height:100%;background:#000;overflow:hidden}
        #p,iframe{position:absolute;top:0;left:0;width:100%;height:100%;border:0}</style>
        </head><body><div id="p"></div>
        <script>
        var player = null, ready = false, queue = [];
        function post(m) { try { window.webkit.messageHandlers.gideon.postMessage(m); } catch (e) {} }
        function run(f) {
          if (!player) return;
          if (f === 'replay') { player.seekTo(0, true); player.playVideo(); return; }
          if (typeof player[f] === 'function') player[f]();
        }
        function gideon(f) { if (ready) run(f); else queue.push(f); }
        function onYouTubeIframeAPIReady() {
          player = new YT.Player('p', {
            host: 'https://www.youtube-nocookie.com',
            videoId: '\(safeID)',
            playerVars: { playsinline: 1, autoplay: \(autoplay ? 1 : 0), mute: \(muted ? 1 : 0),
                          controls: \(controls ? 1 : 0), rel: 0, iv_load_policy: 3, fs: 0,
                          disablekb: 1, origin: '\(origin.absoluteString)' },
            events: {
              onReady: function (e) {
                ready = true;
                \(muted ? "e.target.mute();" : "e.target.unMute();")
                \(autoplay ? "e.target.playVideo();" : "")
                var q = queue; queue = []; q.forEach(run);
                post('ready');
              },
              onStateChange: function (e) {
                if (e.data === 1) post('playing');
                else if (e.data === 2) post('paused');
                else if (e.data === 0) post('ended');
              },
              onError: function () { post('failed'); }
            }
          });
        }
        </script>
        <script src="https://www.youtube.com/iframe_api"></script>
        </body></html>
        """
    }
}
