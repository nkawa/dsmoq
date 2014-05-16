package dsmoq.framework;

import dsmoq.framework.types.Stream;
import dsmoq.framework.types.Location;

typedef ApplicationContext = {
    var location(default, null): Stream<Location>;
}