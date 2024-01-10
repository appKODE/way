package ru.kode.way

interface NodeBuilder {
  /**
   * Given a [path] builds all nodes which correspond to path segments.
   *
   * For example given a "appFlow.loginFlow.credentials.otp" will build 2 flow nodes and 2 screen nodes.
   *
   * They will be put in cache, so if the request to build "appFlow.loginFlow.credentials.success" will come, only
   * "success" node will be built.
   *
   * @param path target path
   * @param payloads payloads for nodes in path which have parameters
   * @param rootSegmentAlias a segment which, when passed, should be used to replace root segment for this node
   * builder. This is used when several node builders are composed. For example:
   *
   * ```
   * // in app-flow.dot
   * appFlow -> loginUserFlow
   *
   * // in login-flow.dot, 'loginFlow' has different name
   * loginFlow -> credentials
   * ```
   *
   * when these schemas are composed, AppFlowNodeBuilder will request child node builder to build a "loginUserFlow"
   * node, but neither LoginFlowNodeBuilder nor LoginFlowSchema know nothing that "AppFlow" has decided to call
   * "loginFlow" as "loginUserFlow", so AppFlowNodeBuilder will path a
   * `rootSegmentAlias = Segment("loginUserFlow", ...)` to inform LoginFlowNodeBuilder about this name
   */
  fun build(path: Path, payloads: Map<Path, Any>, rootSegmentAlias: Segment?): Node
  fun invalidateCache(path: Path)

  val schema: Schema
}
