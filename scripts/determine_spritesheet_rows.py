import os
from PIL import Image

SPRITES_DIR = "C:/Users/Thijssenj/Projects/BattalionRevival/battalion-browser/public/assets/units/attack"

def determine_spritesheet_rows(filename):
    img_path = os.path.join(SPRITES_DIR, filename)
    img = Image.open(img_path).convert("RGBA")
    pixels = img.load()
    width, height = img.size
    # Scan the image row-wise to identify non-transparent regions separated by fully transparent bands
    # A "row" here means a group of consecutive horizontal lines separated by an all-alpha-0 band

    band_transparent = [all(pixels[x, y][3] == 0 for x in range(width)) for y in range(height)]
    bands = []
    current_band = None

    for y, is_trans in enumerate(band_transparent):
        if not is_trans:
            if current_band is None:
                current_band = [y, y]
            else:
                current_band[1] = y
        else:
            if current_band is not None:
                bands.append(tuple(current_band))
                current_band = None

    if current_band is not None:
        bands.append(tuple(current_band))
    
    # Store results in global dict for JSON output
    if not hasattr(determine_spritesheet_rows, "_results"):
        determine_spritesheet_rows._results = {}
    # Strip file extension and use the base filename as the unit name
    unit_name = os.path.splitext(os.path.basename(filename))[0]
    determine_spritesheet_rows._results[unit_name] = len(bands)

def write_json_output():
    import json
    results = getattr(determine_spritesheet_rows, "_results", {})
    out_path = os.path.join(SPRITES_DIR, "attack_rows.json")
    with open(out_path, "w") as f:
        json.dump(results, f, indent=2)

    

def main():
    for filename in os.listdir(SPRITES_DIR):
        if not filename.lower().endswith((".png", ".jpg", ".jpeg")):
            continue
        determine_spritesheet_rows(filename)
        print(f"Processed: {filename}")
    write_json_output()
if __name__ == "__main__":
    main()