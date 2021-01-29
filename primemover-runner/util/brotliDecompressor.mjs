const zlib = require('zlib')

// Encode the buffer in base 64. UCS2 will work, but UTF8 will not.
const encoding = "base64";

function compress (data) {
  zlib.brotliCompress(data, {
    params: {
      [zlib.constants.BROTLI_PARAM_MODE]: zlib.constants.BROTLI_MODE_TEXT
    }
  }, (err, data) => {
    if (err) {
      console.error("brotliCompress: An error occurred:", err);
    } else {
      console.log("Compressed data:", data.toString("base64"));
      console.log('-------------------------------')
      decompress(data.toString(encoding), encoding);
    }
  })
}

function decompress (encodedData, encoding) {
  const compressedData = Buffer.from(encodedData, encoding);
  zlib.brotliDecompress(compressedData, (err, data) => {
    if (err) {
      console.error("brotliDecompress: An error occurred:", err);
    } else {
      console.log("Decompressed data:", data.toString("utf8"));
    }
  });
}

const input = "g8wDAGSqzeq/n1eq987DTiPRUyBEMI1WCgvBfOmtGbqZtjEgCmgvCjQM1+AEssKyKCwNKKHETrD+N5TOfQfFpJvBx4SzsLn5++Pz1y+32w20cVCE0+yXgX1ZBubUcn4jVovH/AdAb7AFhgVX1wGKwRbQw1D6bhe2rdsNVaf9AaL22bYV2IKQRwnViSpuhicZtpMAS3RRzVj0F9Jk9Z3NJicRPuVJ42j/PJ5IGnzOAWrBFry7rmq3Hx9h4TdT1YUB1nkF/Ba1ndvF/je/zD/i3I3C9r/63kNIBl38pS0tB5uDb3g2zhn8IjlPiEj99VtfHnhujanYwX2WA+kohOhdIMNgOU0ZTtwHxNG8Nurl3kNILNt7DyEZ4BB8PuY3MEHcxWFzlFycgctBXVc2IRJ4d9EHJ7N+ClK+g1Q8KKaUiY2tlZO0bidr7OiIGaox6c630XhVrWaOuoRPL+oyeV7e8eXVoASzXhyo7+V1dcQUvUFBu+T43TpeOaVhvzepmG7H926FkonMujHuurAxePeP5oM5HKtMrMLtGIZFOIFs4bCxFsIQonMT+2FpLfhjs24UaQknkcIS4FFvQriHEUszWq7k26C9Zfw7tQq95eb1NJnTC63PjFCeacyhGuU1OBVDsjXfkntmbE7q0R8KZpiYUXmMLKAieHlXz6yL85Auw7GBV+ZG8B+EstiLs9jlLWyMpodR3HbrXPFiQZaiUjDWk3QobrLvgY5SJCApkxia5fZRzut90HaK9u75IeDDXq7aC243u+tTLNcvEVkXRQ8UStoFpe6d3NKXIwOWq+O6gvhJ7MRn8vs8j4uoBWOwHjZHZt3Mj90VD9lbyIeIq5EpBqc2HclNkK9WiAM+8kkZce3AROxLp2tj5f/OkeUfOOK51qmnRJqOtSmGjOMPb+z9hvpwxJJX7Z0xyVLL0RXYVGlYYXUOMv9esVNVZRrmblWGA2ZNmYe0nZn+uP5eAJsXeHBqklMto2u0UU51kyLewK1yajcyozF6MgY7sq/DpBrJiWBUW4kfZTKWFDiryWogYZljpPyVX6fQXYhJGa98XkrE/MgLxkXlNNFTPqAGW0D12OgSa7KSd86w77PAGW2jmpCDXcDnwwAD";

decompress(input, encoding);