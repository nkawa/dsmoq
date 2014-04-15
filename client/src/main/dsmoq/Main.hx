package dsmoq;

import dsmoq.framework.Engine;
import dsmoq.pages.Definitions;

class Main {
    public static function main() {
        new Engine(Definitions.application()).start();
    }
}
