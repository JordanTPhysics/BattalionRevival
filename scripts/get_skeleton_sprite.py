import os
from PIL import Image

SPRITES_DIR = "C:/Users/Thijssenj/Projects/BattalionRevival/battalion-browser/public/assets/units/attack"
OUTPUT_DIR = "C:/Users/Thijssenj/Projects/BattalionRevival/battalion-browser/public/assets/units/attack/uncoloured"

os.makedirs(OUTPUT_DIR, exist_ok=True)

def process_sprite(filename):
    img_path = os.path.join(SPRITES_DIR, filename)
    img = Image.open(img_path).convert("RGBA")
    pixels = img.load()
    width, height = img.size

    for y in range(height):
        for x in range(width):
            r, g, b, a = pixels[x, y]
            if r > g * 1.6 and r > b * 1.6:
                pixels[x, y] = (1, g, b, a)

    filename = filename.split("-")[-1].lower()
    output_path = os.path.join(OUTPUT_DIR, filename)
    img.save(output_path)

def main():
    for filename in os.listdir(SPRITES_DIR):
        if not filename.lower().endswith((".png", ".jpg", ".jpeg")):
            continue
        process_sprite(filename)
        print(f"Processed: {filename}")

if __name__ == "__main__":
    main()