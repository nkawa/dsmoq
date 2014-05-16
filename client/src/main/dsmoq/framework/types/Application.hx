package dsmoq.framework.types;

import dsmoq.framework.ApplicationContext;

/**
 * @author terurou
 */
typedef Application<TPage: EnumValue> = {
    function bootstrap(): Promise<Unit>;
    function frame(context: ApplicationContext): PageFrame<TPage>;
    function content(page: TPage): PageContent<TPage>;
    function toLocation(page: TPage): Location;
    function fromLocation(location: Location): Option<TPage>;
}