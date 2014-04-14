package specs.framework.helpers;

import js.expect.Expect;
import js.mocha.Mocha;

using js.expect.Expect;
using js.mocha.Mocha;

import dsmoq.framework.helpers.*;

enum TestEnum { A; B; C(x:Int); }

class CoreSpec {
    public function new(){
        M.describe("Core basic functions", function(){
            M.it("identity", function(){
                E.expect(Core.identity(1)).toBe(1);
                // E.expect(Core.identity({a:1})).toBe({a:1});
                E.expect(Core.identity({a:1})).to.eql({a:1});
                var v = {a: 1};
                v.a = 3;
                E.expect(Core.identity(v)).to.eql({a: 3});
                E.expect(Core.identity(v)).not.to.eql({a: 1});
                E.expect(Core.identity([1,2,3])).to.eql([1,2,3]);
                E.expect(Type.enumEq(Core.identity(A), A)).to.be.ok();
                E.expect(Core.identity(A)).to.enumEqual(A);
                E.expect(Core.identity(C(3))).to.enumEqual(C(3));
                E.expect(Core.identity(C(3))).not.to.enumEqual(C(2));
                function f(x){return x + 1;}
                E.expect(Core.identity(f)).toBe(f);
                E.expect(Core.identity(Core.identity)(f)).toBe(f);

                E.expect(Core.identity(1)).not.toBe("1");
            });

            M.it("effect", function(){
                var x = false;
                function sideEffect(a: Bool){ x = a; }
                E.expect(Core.effect(sideEffect)(true)).to.be.ok();
                E.expect(x).to.be.ok();
            });

            M.it("nop", function(){
                function f1(f: Void -> Int){return f();}
                function f2(f: Void -> TestEnum){return f();}
                function f3(f: Void -> {a: Int, b: TestEnum}){return f();}
                E.expect(f1(Core.nop)).toBe(null);
                E.expect(f2(Core.nop)).toBe(null);
                E.expect(f3(Core.nop)).toBe(null);
            });
        });

        M.describe("Core toState", function(){
            M.it("empty", function(){
                E.expect(Core.toState([])()).to.eql([]);
            });

            M.it("side effect", function(){
                var x = 0;
                function f1(){ x ++; return x;}
                function f2(){ return 3;}
                E.expect(Core.toState([f1,f1,f2])()).to.eql([1,2,3]);
            });
        });

        M.describe("Core merge", function(){
            M.it("empty", function(){
                E.expect(Core.merge({}, {})).to.be.eql({});
            });
            M.it("merge", function(){
                var a1 = {a: 1, b: "a"};
                var a2 = {c: 3.0};
                var b = Core.merge(a1, a2);

                E.expect(b).to.be.eql({a:1, b: "a", c: 3.0});
            });

            M.it("nested", function(){
                var a1 = {a: 1, b: "a"};
                var a2 = {c: 3.0};
                var a3 = {d: a1, e: a2};
                var b2 = Core.merge(a3, {f: C(3)});
                E.expect(b2).to.be.eql({d: {a: 1, b: "a"}, e: {c: 3.0}, f: C(3)});

            });

            M.it("change original", function(){
                var a1 = {a: 1, b: "a"};
                var a2 = {c: 3.0};
                var a3 = {d: a1, e: a2};
                var a4 = {f: C(3)};
                var b = Core.merge(a1, a2);
                var b2 = Core.merge(a3, a4);

                E.expect(b).to.be.eql({a:1, b: "a", c: 3.0});
                b.c = 4.0;
                E.expect(b).to.be.eql({a:1, b: "a", c: 4.0});
                E.expect(a1).to.be.eql({a:1, b: "a"});
                E.expect(a2).to.be.eql({c: 3.0});
                a1.a = 3;
                a4.f = A;
                E.expect(b2).to.be.eql({d: {a: 3, b: "a"}, e: {c: 3.0}, f: C(3)});
            });
        });
    }
}
