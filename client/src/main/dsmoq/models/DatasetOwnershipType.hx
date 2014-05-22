package dsmoq.models;

@:enum abstract DatasetOwnershipType(Int) {
    var User = 1;
    var Group = 2;
}