package com.lynn.nook.ai.agent;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.middleware.MiddlewareBase;
import reactor.core.publisher.Mono;

/**
 * 把 nook 每个 Agent 的 persona 作为人格设定注入到<b>当轮</b>系统提示——persona 文本由
 * {@code AiChatService} 经 {@code SendOptions.withAttribute(PERSONA_ATTR, ...)} 放进
 * {@link RuntimeContext}，此中间件在 {@code onSystemPrompt} 阶段读出并前置。
 *
 * <p>由此整个 nook-ai 只需<b>一个单例</b> {@code HarnessAgent}（{@code sysPrompt} 留空），
 * persona 随请求变化——不再按 agentId 缓存 N 个实例、也没有「persona 改了要重建运行时」的问题，
 * 符合 agentscope 官方「agent 单例 + 每请求经 RuntimeContext 标识」的推荐用法。
 *
 * <p>{@link #order()} 取 2（高于框架默认 1）：本中间件在 {@code WorkspaceContextMiddleware}
 * 之外一层执行，收到的 {@code currentPrompt} 已含工作区 / MEMORY.md 上下文，persona 前置即可
 * 得到「人格在前、运行上下文在后」的系统提示。
 */
public class PersonaMiddleware implements MiddlewareBase {

    /** RuntimeContext 属性 key：当轮 persona 文本。 */
    public static final String PERSONA_ATTR = "nook.persona";

    private static final int ORDER = 2;

    @Override
    public Mono<String> onSystemPrompt(Agent agent, RuntimeContext ctx, String currentPrompt) {
        String persona = ctx == null ? null : ctx.get(PERSONA_ATTR);
        if (persona == null || persona.isBlank()) {
            return Mono.just(currentPrompt == null ? "" : currentPrompt);
        }
        String base = currentPrompt == null ? "" : currentPrompt;
        return Mono.just(base.isBlank() ? persona : persona + "\n\n" + base);
    }

    @Override
    public int order() {
        return ORDER;
    }
}
