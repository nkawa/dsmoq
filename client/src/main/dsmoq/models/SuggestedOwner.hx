package dsmoq.models;

typedef SuggestedOwner = {
    var dataType(default, null): DatasetOwnershipType;
    var id(default, null): String;
    var name(default, null): String;
    var fullname(default, null): Null<String>;
    var organization(default, null): Null<String>;
    var image(default, null): String;
}