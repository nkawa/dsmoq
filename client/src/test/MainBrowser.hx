package;

import js.mocha.Mocha;
using js.mocha.Mocha;

/**
 * ...
 * @author Richard Janicek
 */

class MainBrowser {
    static function main() {
        Mocha.setup( { ui: Ui.BDD } );

        //new specs.framework.helpers.CoreSpec();
        //new specs.framework.helpers.PromisesSpec();
        new specs.js.support.OptionSpec();
        Mocha.run();
    }
}
