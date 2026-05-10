import os
from PIL import Image

PATTERN_IMG_PATH = "C:/Users/Thijssenj/Projects/BattalionRevival/battalion-browser/public/assets/custom/camo.png"
TARGET_SPRITES_DIR = "C:/Users/Thijssenj/Projects/BattalionRevival/battalion-browser/public/assets/units/uncoloured"
OUTPUT_DIR = "C:/Users/Thijssenj/Projects/BattalionRevival/battalion-browser/public/assets/units/heni"

def paint_pattern_to_sprite(pattern_img_path, target_sprites_dir, output_dir):
    """
    Paints the pattern from pattern_img_path onto all sprites in target_sprites_dir,
    only applying to non-transparent pixels whose red value is not 1.
    """
    os.makedirs(output_dir, exist_ok=True)

    # Load the pattern and ensure it's RGBA
    pattern_img = Image.open(pattern_img_path).convert("RGBA")

    pattern_pixels = None
    for filename in os.listdir(target_sprites_dir):
        if not filename.lower().endswith((".png", ".jpg", ".jpeg")):
            continue

        target_path = os.path.join(target_sprites_dir, filename)
        img = Image.open(target_path).convert("RGBA")
        pixels = img.load()
        width, height = img.size
        
        pattern_img = pattern_img.resize((width, height), resample=Image.NEAREST)
        pattern_pixels = pattern_img.load()

        for y in range(height):
            for x in range(width):
                r, g, b, a = pixels[x, y]
                if a == 0:
                    continue
                if r != 1:
                    continue
                pr, pg, pb, pa = pattern_pixels[x, y]
                # Blend pattern color into the sprite, preserving original alpha
                pixels[x, y] = (pr, pg, pb, a)

        output_path = os.path.join(output_dir, filename)
        img.save(output_path)
        print(f"Pattern painted to: {filename}")

def main():

    paint_pattern_to_sprite(PATTERN_IMG_PATH, TARGET_SPRITES_DIR, OUTPUT_DIR)

if __name__ == "__main__":
    main()