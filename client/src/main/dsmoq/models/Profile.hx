package dsmoq.models;

typedef Profile = {
    var id(default, never): String;
    var name(default, never): String;
    var fullname(default, never): String;
    var organization(default, never): String;
    var title(default, never): String;
    var image(default, never): String;
    var isGuest(default, never): Bool;
}