package specs.dsmoq.framework.types;

import js.expect.Expect;
import js.mocha.Mocha;
using js.expect.Expect;
using js.mocha.Mocha;

import dsmoq.framework.types.Promise;

class PromisesSpec {
    public function new() {
        M.describe("Promises basic functions", function() {


            M.it("connect", function(done){
                var p1 = new Promise();
                var p2 = new Promise();
                Promises.connect(p1, p2);

                p1.resolve(3);

                p2.then(function(x){
                    E.expect(x).to.be.equal(3);
                    done();
                });
            });

            M.it("oneOf", function(done){
                var p1 = new Promise();
                var p2 = new Promise();
                var p3 = new Promise();

                var p4 = Promises.oneOf([p1,p2,p3]);

                p4.then(function(x){
                    E.expect(x).to.be.equal(3);
                    done();
                });
                p2.resolve(3);
                // throw AlreadyResolvedException from p4, but I don't know how to write test.
                // p1.resolve(1);
            });

            M.it("tap", function(done){
                var p1 = Promises.tap(function(p){
                    p.resolve("a");
                });

                p1.then(function(x){
                    E.expect(x).to.be.equal("a");
                    done();
                });
            });
        });

    }
}
