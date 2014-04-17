package dsmoq.framework.helpers;

import promhx.Stream;

/**
 * ...
 * @author terurou
 */
class Streams {

    public static function oneOf<A>(streams: Array<Stream<A>>): Stream<A> {
        // TODO 暫定対応。そもそもガードすることがおかしい
        var stream = new Stream();
        var notResolved = true;
        Lambda.iter(streams, function(x) {
            x.then(function (y) {
                if (notResolved) {
                    stream.resolve(y);
                    notResolved = false;
                }
            });
        });
        return stream;
    }

}