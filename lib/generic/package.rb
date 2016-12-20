module Synthea
  module Generic
    class Package
      attr_reader :name, :main, :submodules

      def initialize(package_path)
        @path = package_path
        @name = get_name(package_path)

        files = Dir.glob(get_path('*.json'))
        main_file = get_path(@name + '.json')
        # The package must include a JSON module file with the same name.
        # That file is the "main" file for the package and its context is
        # run first.
        unless files.include?(main_file)
          raise "No main module \"#{@name}.json\" found in package \"#{@name}\""
        end

        files.each do |filename|
          cfg = get_config(filename)
          unless cfg['package'] && cfg['package'] == @name
            raise "Module \"#{context.name}\" must be part of package \"#{@name}\""
          end
          if filename == main_file
            @main = cfg
          else
            @submodules ||= {}
            @submodules[cfg['name']] = cfg
          end
        end
      end

      def get_config(fullpath)
        cfg = JSON.parse(File.read(fullpath))
        puts "Loaded \"#{cfg['name']}\" module from package \"#{@name}\""
        cfg
      end

      def get_path(filename)
        File.join(@path, filename)
      end

      def get_name(package_path)
        package_path[%r{\/(\w+)\/*$}, 1]
      end
    end
  end
end
