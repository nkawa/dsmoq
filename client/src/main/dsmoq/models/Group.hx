package dsmoq.models;

typedef Group = {
    var id(default, never): String;
    var name(default, never): String;
    var description(default, never): String;
    var images(default, never): Array<Image>;
    var primaryImage(default, never): String;
    var isMember(default, never): Bool;
    var role(default, never): GroupRole;
}