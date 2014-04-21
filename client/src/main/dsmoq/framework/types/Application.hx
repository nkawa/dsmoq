package dsmoq.framework.types;

/**
 * @author terurou
 */
typedef Application<TPage: EnumValue> = {
    initialize: Void -> PageContainer<TPage>,
    toUrl: TPage -> PageInfo,
    fromUrl: PageInfo -> TPage,
    render: TPage -> Option<Html> -> PageComponent<TPage>
}