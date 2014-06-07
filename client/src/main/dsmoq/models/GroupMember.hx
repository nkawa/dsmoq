package dsmoq.models;

typedef GroupMember = {
    var id(default, never): String;
    var name(default, never): String;
    var fullname(default, never): String;
    var organization(default, never): String;
    var title(default, never): String;
    var image(default, never): String;
    var role(default, never): GroupRole;
}