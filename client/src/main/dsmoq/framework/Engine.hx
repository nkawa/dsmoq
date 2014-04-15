package dsmoq.framework;

import dsmoq.framework.Types;
import dsmoq.framework.Effect;

class Engine<Page> {
    var application: Application<Page>;
    var currentPage: Page;
    var container: Replacable<Page, Void, Void>;

    public function new(application) {
        this.application = application;
    }

    public function start() {
        Effect.initialize(renderPage);
        currentPage = application.fromUrl(Effect.global().location());
        container = application.initialize(currentPage);
    }

    private function renderPage(location: PageInfo, needRender: Bool) : Void {
        if (container != null) {
            var newPage = application.fromUrl(location);
            if (!Type.enumEq(newPage, currentPage)) {
                currentPage = newPage;
                if (needRender) container.put(currentPage);
            }
        }
    }
}
