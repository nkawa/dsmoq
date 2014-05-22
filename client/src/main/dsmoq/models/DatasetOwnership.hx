package dsmoq.models;

typedef DatasetOwnership = {
    var id(default, null): String;
    var ownerType(default, null): DatasetOwnershipType;
    var name(default, null): String;
    var image(default, null): String;
}
