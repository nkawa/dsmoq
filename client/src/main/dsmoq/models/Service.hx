package dsmoq.models;

import dsmoq.framework.types.Promise;
import dsmoq.framework.types.Promise;
import dsmoq.framework.types.DeferredStream;
import dsmoq.framework.types.PromiseStream;
import dsmoq.framework.types.Unit;
import js.jqhx.JQuery;
using dsmoq.framework.helper.PromiseTools;

/**
 * ...
 * @author terurou
 */
@:keep
class Service {
    //static function __init__() {
        //var init = new Promise();
        //bootstrap = init.toPromise();
//
        //rawEvent = new DeferredStream();
//
        //JQuery.getJSON("/api/profile").done(function (x) {
            //init.resolve(Unit._);
            //trace(x);
        //}).fail(function (e) {
//
        //});
    //}

    public static var bootstrap(default, null) = new Promise<Service>(function (resolve, reject) {
        JQuery.getJSON("/api/profile").then(function (x) {
            resolve(new Service(x));
        }, reject);
    });

    function new(profile) {

    }

    public function getProfile(): Promise<Profile> {
        return null;
    }

}