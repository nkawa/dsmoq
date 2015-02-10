package dsmoq.models;

typedef StatisticsDetail =
{
	var dataset_amount(default, never): UInt;
    var real_size(default, never): UInt;
    var local_size(default, never): UInt;
    var s3_size(default, never): UInt;
    var total_size(default, never): UInt;
}