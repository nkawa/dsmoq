package dsmoq.models;

@:enum abstract DatasetAccessLevel(Int) {
    var Deny = 0;
    var LimitedPublic = 1;
    var FullPublic = 2;
}