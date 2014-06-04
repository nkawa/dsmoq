package dsmoq.models;

typedef RangeSlice<T> = {
    var summary(default, null): { count: UInt, offset: UInt, total: UInt };
    var results(default, null): Array<T>;
}