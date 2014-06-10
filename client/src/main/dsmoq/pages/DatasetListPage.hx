package dsmoq.pages;

import dsmoq.framework.types.PageContent;
import js.support.ControllableStream;
import js.html.Element;

class DatasetListPage {

    public static function create(): PageContent<Page> {
        return {
            navigation: new ControllableStream(),
            invalidate: function (container: Element) {

            }
        }
    }

}