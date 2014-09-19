package dsmoq;

import hxgnd.PositiveInt;

enum Page {
    Dashboard;

    //DatasetNew;
    DatasetList(page: PositiveInt);
    DatasetShow(id: String);
    DatasetEdit(id: String);

    //GroupNew;
    GroupList(page: PositiveInt);
    GroupShow(id: String);
    GroupEdit(id: String);

    Profile;
}