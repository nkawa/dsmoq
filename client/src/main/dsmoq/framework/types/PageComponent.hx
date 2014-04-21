package dsmoq.framework.types;

/**
 * @author terurou
 */
typedef PageComponent<TPage: EnumValue> = Component<Void, PageEvent<TPage>>;