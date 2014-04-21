package dsmoq.framework;

import dsmoq.framework.types.Application;
import dsmoq.framework.Effect;
import dsmoq.framework.types.PageComponent;
import dsmoq.framework.types.PageContainer;
import dsmoq.framework.types.Replacable;
import dsmoq.framework.types.Html;
import dsmoq.framework.types.PageEvent;
import dsmoq.framework.types.PageInfo;
import dsmoq.framework.types.Option;

class Engine<TPage: EnumValue> {
    var application: Application<TPage>;
    var container: PageContainer<TPage>;

    public function new(application) {
        this.application = application;
    }

    public function start(): Void {
        Effect.initialize(renderPage);

        container = application.initialize();
        container.event.then(handlePageEvent);
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
            content.event.then(handlePageEvent);

            if (container != null) container.put(content);
        }
    }

    private function handlePageEvent(event: PageEvent<TPage>) {
        switch (event) {
            case Navigate(page):
                Effect.global().changeUrl(application.toUrl(page), true);
            case NavigateAsBackword(page):
                // TODO historyを消す
                Effect.global().changeUrl(application.toUrl(page), true);
            case Foward:
                // TODO
            case Backward:
                // TODO
        }
    }
}
