package dsmoq.models;

typedef RangeSlice<T> = {
    var summary(default, null): { count: UInt, offset: UInt, total: UInt };
    var result(default, null): Array<T>;
}