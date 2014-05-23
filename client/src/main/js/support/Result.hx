package js.support;

/**
 * @author terurou
 */
enum Result<TSuccess> {
    Success(x: TSuccess);
    Failure(x: Error);
}