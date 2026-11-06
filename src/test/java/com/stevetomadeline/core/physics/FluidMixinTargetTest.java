package com.stevetomadeline.core.physics;

import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import static org.junit.jupiter.api.Assertions.*;

/** Detect target drift without launching Minecraft or pretending to test runtime injection. */
class FluidMixinTargetTest {
    private static ClassNode read(String name) throws Exception {
        var node = new ClassNode();
        try (InputStream stream = FluidMixinTargetTest.class.getResourceAsStream("/" + name + ".class")) {
            assertNotNull(stream, name);
            new ClassReader(stream).accept(node, ClassReader.SKIP_CODE);
        }
        return node;
    }
    private static Object value(AnnotationNode annotation, String key) {
        for (int i = 0; i < annotation.values.size(); i += 2)
            if (key.equals(annotation.values.get(i))) return annotation.values.get(i + 1);
        return null;
    }
    @Test void fluidHooksAndShadowFieldsExistInTheActualNeoForgeMinecraftBytecode() throws Exception {
        var entity = read("net/minecraft/world/entity/Entity");
        var mixin = read("com/stevetomadeline/mixin/EntityFluidMixin");
        int shadows = 0;
        for (var field : mixin.fields) {
            if (field.visibleAnnotations == null) continue;
            if (field.visibleAnnotations.stream().noneMatch(a -> a.desc.endsWith("/Shadow;"))) continue;
            assertTrue(entity.fields.stream().anyMatch(f -> f.name.equals(field.name) && f.desc.equals(field.desc)), field.name);
            shadows++;
        }
        int selectors = 0;
        for (var method : mixin.methods) {
            if (method.visibleAnnotations == null) continue;
            for (var annotation : method.visibleAnnotations) {
                if (!annotation.desc.endsWith("/Inject;")) continue;
                assertEquals(1, value(annotation, "require"));
                for (Object name : (List<?>) value(annotation, "method")) {
                    String selector = (String) name;
                    assertEquals(1, entity.methods.stream().filter(m -> selector.contains("(")
                            ? selector.equals(m.name + m.desc) : selector.equals(m.name)).count(), selector);
                    selectors++;
                }
            }
        }
        assertEquals(6, selectors);
        assertEquals(6, shadows);
    }
}
