package dsmoq.framework;

import dsmoq.framework.Types;
import dsmoq.framework.Effect;

class Engine<TPage: EnumValue> {
    var application: Application<TPage>;
    var container: Replacable<Html, Void, Void>;

    public function new(application) {
        this.application = application;
    }

    public function start(): Void {
        Effect.initialize(renderPage);

        // TODO コンテナで発生したイベントも拾う（Headerを考慮するため）
        container = application.initialize();
    }

    // TODO needRender消したい（ここで判断するとろくなことにならない）
    // TODO hashの更新含めて、URLのrewriteは全てEngineを通す。EffectをGlobalに持ちたくない。
    private function renderPage(location: PageInfo, needRender: Bool): Void {
        putPage(application.fromUrl(location), needRender);
    }

    private function putPage(page: TPage, needRender: Bool): Void {
        if (needRender) {
            // 同じページへの遷移の場合は描画済みコンテンツをそのまま返す
            var prevPage = application.fromUrl(Effect.global().location());
            var prevHtml = (Type.enumIndex(page) == Type.enumIndex(prevPage))
                            ? Some(container.html) : None;

            var content = application.render(page, prevHtml);

            // Promise->Stream + URL(Hashチェンジ）もイベントで処理を行う
            content.event.then(function (event) {
                switch (event) {
                    case Navigate(p):
                        Effect.global().changeUrl(application.toUrl(p), true);
                    case NavigateAsBackword(p):
                        // TODO historyを消す
                        Effect.global().changeUrl(application.toUrl(p), true);
                    case Foward:
                        // TODO
                    case Backward:
                        // TODO
                }
            });

            if (container != null) container.put(content.html);
        }
    }
}
