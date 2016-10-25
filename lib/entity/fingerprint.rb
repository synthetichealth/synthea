module Synthea
  class Fingerprint
    @width = 100
    @height = 100
    @feature_offsets = [
      [98, 0],
      [98, 98],
      [98, 196],
      [0, 98],
      [196, 98]
    ]

    @path = File.expand_path('../../resources/fingerprint.png', File.dirname(File.absolute_path(__FILE__)))
    @template = ChunkyPNG::Image.from_file(@path)
    @template = @template.extract_mask(ChunkyPNG::Color::BLACK).last

    # Generate an artificial fingerprint
    def self.generate
      fingerprint = ChunkyPNG::Image.new(3 * @width, 3 * @height, ChunkyPNG::Color::WHITE)

      # choose the corners
      case rand(3)
      when 0
        top_right = @template.crop(3 * @width, 0 * @height, @width, @height)
        top_left = top_right.flip_vertically
        bottom_right = top_right.flip_horizontally
        bottom_left = top_left.flip_horizontally
      when 1
        bottom_right = @template.crop(3 * @width, 3 * @height, @width, @height)
        top_right = bottom_right.flip_horizontally
        top_left = top_right.flip_vertically
        bottom_left = top_left.flip_horizontally
      when 2
        bottom_left = @template.crop(0 * @width, 3 * @height, @width, @height)
        bottom_right = bottom_left.flip_vertically
        top_right = bottom_right.flip_horizontally
        top_left = top_right.flip_vertically
      end
      fingerprint.compose!(top_left, 0, 0)
      fingerprint.compose!(top_right, 196, 0)
      fingerprint.compose!(bottom_left, 0, 196)
      fingerprint.compose!(bottom_right, 196, 196)

      # add features
      @feature_offsets.each do |where|
        # get the feature indices
        y = rand(4)
        x = if [0, 3].include?(y)
              rand(1..2) * @width
            else
              rand(4) * @width
            end
        y *= @height
        icon = @template.crop(x, y, @width, @height)
        # add the feature
        fingerprint.compose!(icon, where.first, where.last)
      end

      fingerprint.resample_nearest_neighbor!(128, 128)
    end

    def self.blur(image)
      blur = image.dup
      (1..image.height - 2).each do |j|
        (1..image.width - 2).each do |i|
          l = ChunkyPNG::Color.blend(image.get_pixel(i, j), image.get_pixel(i - 1, j))
          r = ChunkyPNG::Color.blend(image.get_pixel(i, j), image.get_pixel(i + 1, j))
          t = ChunkyPNG::Color.blend(image.get_pixel(i, j), image.get_pixel(i, j - 1))
          b = ChunkyPNG::Color.blend(image.get_pixel(i, j), image.get_pixel(i, j + 1))
          lr = ChunkyPNG::Color.blend(l, r)
          tb = ChunkyPNG::Color.blend(t, b)
          blur.set_pixel(i, j, ChunkyPNG::Color.to_grayscale(ChunkyPNG::Color.blend(lr, tb)))
        end
      end
      blur
    end
  end
end
