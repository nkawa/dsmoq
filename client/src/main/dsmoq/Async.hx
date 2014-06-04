package dsmoq;

enum Async<A> {
    Pending;
    Completed(a: A);
}