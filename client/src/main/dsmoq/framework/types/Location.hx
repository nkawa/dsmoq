package dsmoq.framework.types;

/**
 * @author terurou
 */
typedef Location = {
    path: String,
    ?query: Map<String, String>,
    ?hash: String
}