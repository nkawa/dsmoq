package dsmoq.models;

@:enum abstract DatasetPermission(Int) {
    var Deny = 0;
    var LimitedRead = 1;
    var Read = 2;
    var Write = 3;
}
