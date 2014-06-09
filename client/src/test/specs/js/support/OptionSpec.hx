package specs.js.support;

import js.expect.Expect;
import js.mocha.Mocha;
using js.expect.Expect;
using js.mocha.Mocha;

import js.support.Option;
using js.support.OptionTools;
import js.support.Result;

class OptionSpec {
    public function new() {
        M.describe("get", function(){
            M.it("Some(1)", function(){
                E.expect(Some(1).get()).to.be.equal(1);
            });
            M.it("Some([1,2,3])", function(){
                E.expect(Some([1,2,3]).get()).to.be.eql([1,2,3]);
            });
            M.it("None", function(){
                E.expect(function(){None.get();}).to.throwException();
            });
        });

        M.describe("isEmpty", function(){
            M.it("Some(1)", function(){
                E.expect(Some(1).isEmpty()).toBe(false);
            });
            M.it("None", function(){
                E.expect(None.isEmpty()).toBe(true);
            });
        });

        function inc(x: Int){
            return x + 1;
        }

        function toString(x: Int){
            return Std.string(x);
        }

        function shouldNone<A>(v: Option<A>){  // Noneのassertの仕方が分からなかったので、文字列にした
            E.expect(Std.string(v)).to.be.equal("None");
        }

        M.describe("map", function(){
            M.it("Some(3)", function(){
                E.expect(Some(3).map(inc).get()).to.be.equal(4);
            });
            M.it("Some(3) to String", function(){
                E.expect(Some(3).map(toString).get()).to.be.equal("3");
            });
            M.it("None", function(){
                shouldNone(None.map(inc));
            });
        });

        M.describe("filter", function(){
            function isEven(x:Int){
                return x % 2 == 0;
            }
            M.it("Some(4) to Some", function(){
                E.expect(Some(4).filter(isEven).get()).toBe(4);
            });
            M.it("Some(5) to None", function(){
                shouldNone(Some(5).filter(isEven));
            });
            M.it("None to None", function(){
                shouldNone(None.filter(isEven));
            });
        });

        M.describe("orElse", function(){
            M.it("Some orElse Some", function(){
                E.expect(Some(3).orElse(function(){return Some(5);}).get()).toBe(3);
            });
            M.it("Some orElse None", function(){
                E.expect(Some(3).orElse(function(){return None;}).get()).toBe(3);
            });
            M.it("None orElse Some", function(){
                E.expect(None.orElse(function(){return Some(5);}).get()).toBe(5);
            });
            M.it("None orElse None", function(){
                shouldNone(None.orElse(function(){return None;}));
            });
        });
        M.describe("or", function(){
            M.it("Some or Some", function(){
                E.expect(Some(3).or(Some(5)).get()).toBe(3);
            });
            M.it("Some or None", function(){
                E.expect(Some(3).or(None).get()).toBe(3);
            });
            M.it("None or Some", function(){
                E.expect(None.or(Some(5)).get()).toBe(5);
            });
            M.it("None or None", function(){
                shouldNone(None.or(None));
            });
        });

        M.describe("toResult", function(){
            var error = new js.Error("test error");
            M.it("to Success", function(){
                E.expect(Some(3).toResult(error)).to.enumEqual(Success(3));
            });
            M.it("to Failure", function(){
                E.expect(None.toResult(error)).to.enumEqual(Failure(error));
            });
        });
        M.describe("toArray", function(){
            M.it("Some to Array", function(){
                E.expect(Some(3).toArray()).to.eql([3]);
            });
            M.it("None to Array", function(){
                E.expect(None.toArray()).to.eql([]);
            });
        });

        M.describe("getOrDefault", function(){
            M.it("Some", function(){
                E.expect(Some(3).getOrDefault(10)).toBe(3);
            });
            M.it("None", function(){
                E.expect(None.getOrDefault(10)).toBe(10);
            });
        });
        M.describe("getOrElse", function(){
            function ten(){return 10;}

            M.it("Some", function(){
                E.expect(Some(3).getOrElse(ten)).toBe(3);
            });
            M.it("None", function(){
                E.expect(None.getOrElse(ten)).toBe(10);
            });
        });

        M.describe("flatMap", function(){
            M.it("Some to Some", function(){
                E.expect(Some(3).flatMap(function(x){return Some(x + 1);}).get()).toBe(4);
            });
            M.it("Some to None", function(){
                shouldNone(Some(3).flatMap(function(x){return None;}));
            });
            M.it("None to Some", function(){
                shouldNone(None.flatMap(function(x){return Some(3);}));
            });
            M.it("None to None", function(){
                shouldNone(None.flatMap(function(x){return None;}));
            });
        });
    }
}
