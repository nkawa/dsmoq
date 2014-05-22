package dsmoq.framework.types;

abstract PositiveInt(Int) from Int to Int {

    inline function new(x: Int) {
        this = (x > 0) ? x : 1;
    }

}