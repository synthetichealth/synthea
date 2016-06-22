require_relative '../test_helper'

class EventListTest < Minitest::Test

  def setup
    @entity = Synthea::Entity.new
    @entity.events.create(10,:foo,:setup)
    @entity.events.create(30,:bar,:setup)
    @entity.events.create(50,:foo,:setup)    
    @entity.events.create(100,:foo,:setup)    
    @entity.events.create(150,:foo,:setup)    
  end

  def test_entity_had_event_true
    result = @entity.had_event?(:foo)
    assert(result)
  end

  def test_entity_had_event_false
    result = @entity.had_event?(:baz)
    assert(result==false)
  end

  def test_unprocessed
    result = @entity.events.unprocessed
    assert(result)
    assert(result.is_a?(Array))
    assert(result.first.type==:foo)
  end

  def test_process
    list = @entity.events.unprocessed_since(0,:bar)
    assert(list)
    assert(list.is_a?(Array))
    assert(list.first.type==:bar)
    assert(list.length==1)

    @entity.events.process(list.first)

    result = @entity.events.unprocessed_since(0,:bar)
    assert(result)
    assert(result.is_a?(Array))
    assert(result.empty?)
  end

  def test_unprocessed_before_first
    result = @entity.events.unprocessed_before(20,:foo)
    assert(result)
    assert(result.is_a?(Array))
    assert(result.first.type==:foo)
    assert(result.length==1)
  end

  def test_unprocessed_before
    result = @entity.events.unprocessed_before(120,:foo)
    assert(result)
    assert(result.is_a?(Array))
    assert(result.length==3)
  end

  def test_unprocessed_before_last
    result = @entity.events.unprocessed_before(200,:foo)
    assert(result)
    assert(result.is_a?(Array))
    assert(result.length==4)
  end

  def test_unprocessed_before_empty
    result = @entity.events.unprocessed_before(200,:baz)
    assert(result.empty?)
  end

  def test_unprocessed_since_last
    result = @entity.events.unprocessed_since(120,:foo)
    assert(result)
    assert(result.is_a?(Array))
    assert(result.first.type==:foo)
    assert(result.length==1)
  end

  def test_unprocessed_since
    result = @entity.events.unprocessed_since(50,:foo)
    assert(result)
    assert(result.is_a?(Array))
    assert(result.length==3)
  end

  def test_unprocessed_since_first
    result = @entity.events.unprocessed_since(0,:foo)
    assert(result)
    assert(result.is_a?(Array))
    assert(result.length==4)
  end

  def test_unprocessed_since_empty
    result = @entity.events.unprocessed_since(0,:baz)
    assert(result.empty?)
  end

  def test_before_everything
    result = @entity.events.before(0,:foo)
    assert(result)
    assert(result.is_a?(Array))
    assert(result.empty?)
  end 

  def test_before_first
    result = @entity.events.before(20,:foo)
    assert(result)
    assert(result.is_a?(Array))
    assert(result.first.type==:foo)
    assert(result.length==1)
  end

  def test_before
    result = @entity.events.before(120,:foo)
    assert(result)
    assert(result.is_a?(Array))
    assert(result.length==3)
  end

  def test_before_last
    result = @entity.events.before(200,:foo)
    assert(result)
    assert(result.is_a?(Array))
    assert(result.length==4)
  end

  def test_before_empty
    result = @entity.events.before(200,:baz)
    assert(result.empty?)
  end

  def test_since_last
    result = @entity.events.since(120,:foo)
    assert(result)
    assert(result.is_a?(Array))
    assert(result.first.type==:foo)
    assert(result.length==1)
  end

  def test_since
    result = @entity.events.since(50,:foo)
    assert(result)
    assert(result.is_a?(Array))
    assert(result.length==3)
  end

  def test_since_first
    result = @entity.events.since(0,:foo)
    assert(result)
    assert(result.is_a?(Array))
    assert(result.length==4)
  end

  def test_since_empty
    result = @entity.events.since(0,:baz)
    assert(result.empty?)
  end

end