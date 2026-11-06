package com.stevetomadeline.client;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;
import static org.junit.jupiter.api.Assertions.*;

/** Hidden GL context only; compiles shipped shaders with actual Minecraft includes and reads pixels. */
class ForegroundShaderTest {
    private static String source(String extension) throws Exception {
        String text;
        try (InputStream stream = ForegroundShaderTest.class.getResourceAsStream(
                "/assets/stevetomadeline/shaders/core/foreground_fade." + extension)) {
            assertNotNull(stream); text = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        Path gradle = Path.of(System.getenv().getOrDefault("GRADLE_USER_HOME", System.getProperty("user.home") + "/.gradle"));
        try (var zip = new ZipFile(gradle.resolve("caches/neoformruntime/artifacts/minecraft_1.21.1_client.jar").toFile())) {
            for (String name : new String[]{"light", "fog"}) {
                String directive = "#moj_import <" + name + ".glsl>";
                if (!text.contains(directive)) continue;
                try (InputStream stream = zip.getInputStream(zip.getEntry("assets/minecraft/shaders/include/" + name + ".glsl"))) {
                    text = text.replace(directive, new String(stream.readAllBytes(), StandardCharsets.UTF_8).replace("#version 150", ""));
                }
            }
        }
        return text;
    }
    private static int compile(int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source); glCompileShader(shader);
        assertEquals(GL_TRUE, glGetShaderi(shader, GL_COMPILE_STATUS), glGetShaderInfoLog(shader));
        return shader;
    }
    @Test void terrainPassesPreserveTheBackgroundAndBlendOnlyRemovedGeometry() throws Exception {
        assertTrue(glfwInit(), "GLFW initialization for hidden shader test");
        glfwDefaultWindowHints(); glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3); glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        long window = glfwCreateWindow(16, 16, "Shader verification", 0, 0);
        assertNotEquals(0, window);
        int program = 0, vertex = 0, fragment = 0, vao = 0, vbo = 0, texture = 0;
        try {
            glfwMakeContextCurrent(window); GL.createCapabilities();
            vertex = compile(GL_VERTEX_SHADER, source("vsh")); fragment = compile(GL_FRAGMENT_SHADER, source("fsh"));
            program = glCreateProgram(); glAttachShader(program, vertex); glAttachShader(program, fragment); glLinkProgram(program);
            assertEquals(GL_TRUE, glGetProgrami(program, GL_LINK_STATUS), glGetProgramInfoLog(program));
            glUseProgram(program);
            vao = glGenVertexArrays(); glBindVertexArray(vao); vbo = glGenBuffers(); glBindBuffer(GL_ARRAY_BUFFER, vbo);
            glBufferData(GL_ARRAY_BUFFER, new float[]{-1,-1,-.5f, 3,-1,-.5f, -1,3,-.5f}, GL_STATIC_DRAW);
            int position = glGetAttribLocation(program, "Position"); glEnableVertexAttribArray(position);
            glVertexAttribPointer(position, 3, GL_FLOAT, false, 12, 0);
            glVertexAttrib4f(glGetAttribLocation(program, "Color"), 1, 1, 1, 1);
            glVertexAttrib2f(glGetAttribLocation(program, "UV0"), .5f, .5f);
            glVertexAttribI2i(glGetAttribLocation(program, "UV2"), 240, 240);
            float[] identity = {1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1};
            glUniformMatrix4fv(glGetUniformLocation(program, "ModelViewMat"), false, identity);
            glUniformMatrix4fv(glGetUniformLocation(program, "ProjMat"), false, identity);
            glUniform4f(glGetUniformLocation(program, "ColorModulator"), 1, 1, 1, 1);
            glUniform1f(glGetUniformLocation(program, "FogStart"), 100);
            glUniform1f(glGetUniformLocation(program, "FogEnd"), 200);
            glUniform1f(glGetUniformLocation(program, "SliceDepth"), .75f);
            texture = glGenTextures(); glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, texture);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 1, 1, 0, GL_RGBA, GL_UNSIGNED_BYTE, new int[]{-1});
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST); glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glUniform1i(glGetUniformLocation(program, "Sampler0"), 0); glUniform1i(glGetUniformLocation(program, "Sampler2"), 0);
            glViewport(0, 0, 16, 16); glDisable(GL_DEPTH_TEST); glDisable(GL_FRAMEBUFFER_SRGB);
            glEnable(GL_BLEND); glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            glClearColor(0, 0, 1, 1);
            for (float opacity : new float[]{0, .25f, .5f, 1}) {
                glClear(GL_COLOR_BUFFER_BIT);
                glUniform1i(glGetUniformLocation(program, "ForegroundPass"), 1);
                glUniform1f(glGetUniformLocation(program, "ForegroundOpacity"), opacity);
                glDrawArrays(GL_TRIANGLES, 0, 3);
                ByteBuffer pixel = BufferUtils.createByteBuffer(4); glReadPixels(8, 8, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, pixel);
                assertEquals(Math.round(255 * opacity), Byte.toUnsignedInt(pixel.get(0)), 2, "actual alpha blending");
                assertEquals(255, Byte.toUnsignedInt(pixel.get(2)), 2, "background survives the fade");
            }
            glClear(GL_COLOR_BUFFER_BIT); glUniform1i(glGetUniformLocation(program, "ForegroundPass"), 0);
            glDrawArrays(GL_TRIANGLES, 0, 3);
            ByteBuffer pixel = BufferUtils.createByteBuffer(4); glReadPixels(8, 8, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, pixel);
            assertEquals(0, Byte.toUnsignedInt(pixel.get(0)), "main pass removes only foreground");
            glUniform1f(glGetUniformLocation(program, "SliceDepth"), .25f); glDrawArrays(GL_TRIANGLES, 0, 3);
            glReadPixels(8, 8, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, pixel);
            assertEquals(255, Byte.toUnsignedInt(pixel.get(0)), "retained terrain stays opaque");
            assertEquals(GL_NO_ERROR, glGetError());
        } finally {
            if (program != 0) glDeleteProgram(program);
            if (vertex != 0) glDeleteShader(vertex); if (fragment != 0) glDeleteShader(fragment);
            if (texture != 0) glDeleteTextures(texture); if (vbo != 0) glDeleteBuffers(vbo); if (vao != 0) glDeleteVertexArrays(vao);
            glfwDestroyWindow(window); glfwTerminate(); GL.setCapabilities(null);
        }
    }
}
