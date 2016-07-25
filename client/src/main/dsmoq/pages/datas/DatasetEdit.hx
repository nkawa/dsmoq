package dsmoq.pages.datas;

import dsmoq.models.DatasetFile;
import dsmoq.models.DatasetGuestAccessLevel;
import dsmoq.models.DatasetMetadata;
import dsmoq.models.DatasetOwnership;
import dsmoq.models.Image;
import dsmoq.models.License;
import dsmoq.models.Profile;

/**
 * DatasetEditPageデータバインディング中の、Dataset部のデータ構造です。
 * 
 * @see dsmoq.models.Dataset
 */
typedef DatasetEditDataset = {
    /** @see dsmoq.models.Dataset.id */
    var id: String;
    /** @see dsmoq.models.Dataset.meta */
    var meta: DatasetMetadata;
    var files: Paged<DatasetFile>;
    var updatedFiles: Array<DatasetFile>;
    /** @see dsmoq.models.Dataset.ownerships */
    var ownerships: Array<DatasetOwnership>;
    /** @see dsmoq.models.Dataset.defaultAccessLevel */
    var defaultAccessLevel: DatasetGuestAccessLevel;
    /** @see dsmoq.models.Dataset.primaryImage */
    var primaryImage: Image;
    /** @see dsmoq.models.Dataset.featuredImage */
    var featuredImage: Image;
    /** @see dsmoq.models.Dataset.localState */
    var localState: Int;
    /** @see dsmoq.models.Dataset.s3State */
    var s3State: Int;
    var errors: {
        meta: {
            name: String,
            description: String,
            license: String,
            attributes: String,
        },
        icon: String,
        files: {
            images: String,
        },
        ownerships: { },
    };
};
/**
 * DatasetEditPageデータバインディング用のデータ構造です。
 */
typedef DatasetEdit = {
    var myself: Profile;
    var licenses: Array<License>;
    var dataset: DatasetEditDataset;
    var owners: Async<Paged<DatasetOwnership>>;
};
