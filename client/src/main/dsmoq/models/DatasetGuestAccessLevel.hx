package dsmoq.models;

@:enum abstract DatasetGuestAccessLevel(Int) {
    var Deny = 0;
    var LimitedPublic = 1;
    var FullPublic = 2;
}