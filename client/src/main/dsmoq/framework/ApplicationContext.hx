package dsmoq.framework;

import js.support.Stream;
import dsmoq.framework.types.Location;

typedef ApplicationContext = {
    var location(default, null): Stream<Location>;
}