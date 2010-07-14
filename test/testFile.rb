require 'test/minirunit'
test_check "Test File:"

# path expansion relative to an in-jar url path with File.join
test_equal("file:/Users/foo/dev/ruby/jruby/lib/jruby-complete.jar!/META-INF/jruby.home/lib/ruby/site_ruby",
           File.expand_path(File.join( "file:/Users/foo/dev/ruby/jruby/lib/jruby-complete.jar!/META-INF/jruby.home/lib/ruby/site_ruby/1.8", "..")))

# path expansion relative to an in-jar url path with File.expand_path
test_equal("file:/Users/foo/dev/ruby/jruby/lib/jruby-complete.jar!/META-INF/jruby.home/lib/ruby/site_ruby",
           File.expand_path("..", "file:/Users/foo/dev/ruby/jruby/lib/jruby-complete.jar!/META-INF/jruby.home/lib/ruby/site_ruby/1.8"))

