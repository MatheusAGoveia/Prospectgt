"""Baixa os extratos configurados. Ex.: python scripts/download-data.py --regions sudeste."""
import argparse
from catalog_data import download, selected_regions

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--regions", nargs="+")
    parser.add_argument("--refresh", action="store_true")
    options = parser.parse_args()
    for region in selected_regions(options.regions):
        download(region, options.refresh)
