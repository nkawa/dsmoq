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
        container = application.initialize();
    }

    private function renderPage(location: PageInfo, needRender: Bool): Void {
        putPage(application.fromUrl(location), needRender);
    }

    private function putPage(page: TPage, needRender: Bool): Void {
        if (needRender) {
            if (container != null) {
                var content = application.draw(page);
                // Promise->Stream + URL(Hashチェンジ）もイベントで処理を行う
                content.event.then(function (x) {
                    Effect.global().changeUrl(application.toUrl(x), true);
                });
                container.put(content.html);
            }
        }
    }
}
