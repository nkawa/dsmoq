package dsmoq.models;

typedef DatasetOwnership = {
    var id(default, null): String;
    var name(default, null): String;
    var fullname(default, null): Null<String>;
    var organization(default, null): Null<String>;
    var image(default, null): String;
    var ownerType(default, null): DatasetOwnershipType;
    var accessLevel(default, null): DatasetPermission;
}
