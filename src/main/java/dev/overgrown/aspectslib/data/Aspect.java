package dev.overgrown.aspectslib.data;

import dev.overgrown.aspectslib.networking.PacketCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * This is the Aspect class, which holds an instance of Aspect derived from files found in data/aspectslib/aspects/{json_file}.json
 * <p>
 * Properties:
 * <li>name: Display name</li>
 * <li>textureLocation: Custom texture path (optional)</li>
 * </p>
 * <br>
 * <p>
 * Serialization:
 * <li>JSON for datapacks</li>
 * <li>Network for client sync</li>
 * </p>
 *
 * @param name            This holds the name of the Aspect, as it should be rendered in displays.
 * @param textureLocation This holds the location of the texture, if the player wishes to override the default texture with one from a specific directory.
 */
public record Aspect(String name, Identifier textureLocation) {
    /**
     * This is the codec for encoding/decoding between Aspects and Json. it currently contains two fields,
     * "name" and "texture location", for the two current data fields.
     */
    public static final Codec<Aspect> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                            Codec.STRING.fieldOf("name").forGetter(Aspect::name),
                            Identifier.CODEC.optionalFieldOf("texture_location", new Identifier("aspectslib", "empty")).forGetter(Aspect::textureLocation)
                    )
                    .apply(instance, Aspect::new)
    );

    /**
     * Gets an aspect by its identifier from the loaded aspects map
     */
    public static Aspect getById(Identifier id) {
        return ModRegistries.ASPECTS.get(id);
    }

    /**
     * The packet codec for network serialization in 1.20.1
     */
    public static final PacketCodec<Aspect> PACKET_CODEC = new PacketCodec<>() {
        @Override
        public Aspect decode(PacketByteBuf buf) {
            String name = buf.readString();
            Identifier textureLocation = buf.readIdentifier();
            return new Aspect(name, textureLocation);
        }

        @Override
        public void encode(PacketByteBuf buf, Aspect value) {
            buf.writeString(value.name());
            buf.writeIdentifier(value.textureLocation());
        }
    };

    /**
     * The constructor for the Aspect Class.
     *
     * @param name            the name of the Aspect.
     * @param textureLocation the texture location used in rendering.
     */
    public Aspect {
    }

    /**
     * This method will get the text component name of the Aspect, defaulting to the name defined in the Json file
     * in the event that any name defined in the lang files is not found.
     *
     * @return the text component containing the name of the Aspect.
     */
    public MutableText getTranslatedName() {
        return Text.translatableWithFallback(getTranslatableKey(), name);
    }

    /**
     * This method constructs a translation key based on the identifier of the items, or the name of the aspect.
     *
     * @return a string translation key to be used in Text#translatable(String key).
     */
    public String getTranslatableKey() {
        if (getIdentifier() != null) {
            return "aspect." + getIdentifier().getNamespace() + "." + getIdentifier().getPath() + ".name";
        } else {
            return "aspect.aspectslib." + this.name.toLowerCase() + ".name";
        }
    }

    /**
     * A getter for the Aspect's name.
     *
     * @return the name of the Aspect
     */
    @Override
    public String name() {
        return this.name;
    }

    /**
     * A getter for the Aspect's identifier.
     *
     * @return the identifier of the Aspect
     */
    private Identifier getIdentifier() {
        // Find identifier by searching through the registry
        for (Map.Entry<Identifier, Aspect> entry : ModRegistries.ASPECTS.entrySet()) {
            if (entry.getValue() == this) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * A getter for the Aspect's texture location.
     * If no texture_location is specified in the JSON, defaults to textures/aspects_icons/{name}.png
     *
     * @return the identifier that points towards the texture used for the Aspect's rendering
     */
    @Override
    public Identifier textureLocation() {
        if (!this.textureLocation.equals(new Identifier("aspectslib", "empty"))) {
            // Custom texture location was specified in JSON
            return this.textureLocation;
        } else if (getIdentifier() != null) {
            // Use the aspect's registry path as texture name
            return new Identifier(this.getIdentifier().getNamespace(), "textures/aspects_icons/" + this.getIdentifier().getPath() + ".png");
        } else {
            // Fallback: use the aspect name in lowercase as texture name
            return new Identifier("aspectslib", "textures/aspects_icons/" + this.name.toLowerCase() + ".png");
        }
    }

    @Override
    public @NotNull String toString() {
        return this.name;
    }
}