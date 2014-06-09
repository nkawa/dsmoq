package specs.js.support;

import js.expect.Expect;
import js.mocha.Mocha;
using js.expect.Expect;
using js.mocha.Mocha;

import js.support.Result;
using js.support.ResultTools;

class ResultSpec {
    public function new() {
        var error = new js.Error("error test");
        M.describe("get", function(){
            M.it("Success(1)", function(){
                E.expect(Success(1).get()).to.be.equal(1);
            });
            M.it("Failure", function(){
                E.expect(function(){Failure(error).get();}).to.be.throwException();
            });
        });
        M.describe("isSuccess", function(){
            M.it("Success(1)", function(){
                E.expect(Success(1).isSuccess()).toBe(true);
            });
            M.it("Failure(1)", function(){
                E.expect(Failure(error).isSuccess()).toBe(false);
            });
        });
        M.describe("map", function(){
            function inc(x:Int){
                return x + 1;
            }
            M.it("Success(1)", function(){
                E.expect(Success(1).map(inc).get()).toBe(2);
            });
            M.it("Success(1) to failure", function(){
                E.expect(Success("1").map(function(x){throw error;})).to.enumEqual(Failure(error));
            });
            M.it("Failure(1)", function(){
                E.expect(Failure(error).map(inc)).to.enumEqual(Failure(error));
            });
        });

        M.describe("flatten", function(){
            M.it("Success(Success(3))", function(){
                E.expect(Success(Success(3)).flatten().get()).toBe(3);
            });
            M.it("Success(Failure(error))", function(){
                E.expect(Success(Failure(error)).flatten()).to.enumEqual(Failure(error));
            });
            M.it("Failure(error))", function(){
                var result: Result<Result<Int>> = Failure(error);
                E.expect(result.flatten()).to.enumEqual(Failure(error));
            });
        });

        M.describe("flatMap", function(){
            M.it("Success to Success", function(){
                E.expect(Success(1).flatMap(function(x){return Success(x+1);}).get()).toBe(2);
            });
            M.it("Success(1) to Failure", function(){
                E.expect(Success("1").flatMap(function(x){return Failure(error);})).to.enumEqual(Failure(error));
            });
            M.it("Success(1) to Failure with throwing", function(){
                E.expect(Success("1").flatMap(function(x){throw error;})).to.enumEqual(Failure(error));
            });
            var failure: Result<Int> = Failure(error);
            M.it("Failure(1)", function(){
                E.expect(failure.flatMap(function(x){throw "hoge";})).to.enumEqual(Failure(error));
            });
            M.it("Failure(1) then Failure", function(){
                E.expect(failure.flatMap(function(x){return Failure(new js.Error("hoge"));})).to.enumEqual(Failure(error));
            });
        });

        M.describe("failureMap", function(){
            M.it("Success(3)", function(){
                E.expect(Success(3).failureMap(function(ex){return Success(4);}).get()).toBe(3);
            });
            M.it("Success(3) then throw", function(){
                E.expect(Success(3).failureMap(function(ex){throw error;}).get()).toBe(3);
            });
            M.it("Failure to Success", function(){
                E.expect(Failure(error).failureMap(function(ex){return Success(ex);}).get()).toBe(error);
            });
            M.it("Failure then throw", function(){
                var failure: Result<Int> = Failure(error);
                var error2 = new js.Error("test error 2");
                E.expect(failure.failureMap(function(ex){throw error2;})).to.enumEqual(Failure(error2));
            });
        });

        M.describe("iter", function(){
            M.it("Success(3)", function(){
                var buf = null;
                Success(3).iter(function(x){buf = x;});
                E.expect(buf).toBe(3);
            });
            M.it("Failure", function(){
                var buf = null;
                Failure(error).iter(function(x){buf = x;});
                E.expect(buf).toBe(null);
            });
        });
        M.describe("failureIter", function(){
            M.it("Success(3)", function(){
                var buf = null;
                Success(3).failureIter(function(x){buf = x;});
                E.expect(buf).toBe(null);
            });
            M.it("Failure", function(){
                var buf = null;
                Failure(error).failureIter(function(x){buf = x;});
                E.expect(buf).toBe(error);
            });
        });
    }
}
