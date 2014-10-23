package dsmoq.models;

typedef User = {
    var id(default, null): String;
    var name(default, null): String;
    var fullname(default, null): String;
    var organization(default, null): String;
    var title(default, null): String;
    var description(default, null): String;
    var image(default, null): String;
}