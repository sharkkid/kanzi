/*
Copyright 2011-2013 Frederic Langlet
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
you may obtain a copy of the License at

                http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package transform

const (
	RESET_THRESHOLD = 64
)

type Payload struct {
	previous *Payload
	next     *Payload
	value    byte
}

type MTFT struct {
	size    uint
	lengths []int      // size 16
	buckets []byte     // size 256
	heads   []*Payload // size 16
}

func NewMTFT(sz uint) (*MTFT, error) {
	this := new(MTFT)
	this.size = sz
	this.heads = make([]*Payload, 16)
	this.lengths = make([]int, 16)
	this.buckets = make([]byte, 256)

	// Initialize the linked lists: 1 item in bucket 0 and 17 in each other
	previous := &Payload{value: 0}
	this.heads[0] = previous
	this.lengths[0] = 1
	this.buckets[0] = 0
	listIdx := byte(0)

	for i := 1; i < 256; i++ {
		current := &Payload{value: byte(i)}

		if (i-1)%17 == 0 {
			listIdx++
			this.heads[listIdx] = current
			this.lengths[listIdx] = 17
		}

		this.buckets[i] = listIdx
		previous.next = current
		current.previous = previous
		previous = current
	}

	return this, nil
}

func (this *MTFT) Size() uint {
	return this.size
}

func (this *MTFT) SetSize(sz uint) bool {
	this.size = sz
	return true
}

func (this *MTFT) Forward(input []byte) []byte {
	this.balanceLists(true)
	end := uint(len(input))

	if this.size != 0 {
		end = this.size
	}

	// This section is in the critical speed path 
	return this.moveToFront(input, end)
}

func (this *MTFT) Inverse(input []byte) []byte {
	indexes := this.buckets

	for i := range indexes {
		indexes[i] = byte(i)
	}

	end := uint(len(input))

	if this.size != 0 {
		end = this.size
	}

	for i := uint(0); i < end; i++ {
		idx := input[i]
		value := indexes[idx]
		input[i] = value

		if idx != 0 {
			copy(indexes[1:], indexes[0:idx])
			indexes[0] = value
		}

	}

	return input
}

// Recreate one list with 1 item and 15 lists with 17 items
// Update lengths and buckets accordingly. This is a time consuming operation
func (this *MTFT) balanceLists(resetValues bool) {
	this.lengths[0] = 1
	listIdx := byte(0)
	p := this.heads[0].next

	if resetValues == true {
		this.heads[0].value = byte(0)
		this.buckets[0] = 0
	}

	n := 0

	for i := 1; i < 256; i++ {
		if resetValues == true {
			p.value = byte(i)
		}

		if n == 0 {
			listIdx++
			this.heads[listIdx] = p
			this.lengths[listIdx] = 17
		}

		this.buckets[int(p.value)&0xFF] = listIdx
		p = p.next
		n++

		if n > 16 {
			n = 0
		}
	}
}

func (this *MTFT) moveToFront(values []byte, end uint) []byte {
	previous := this.heads[0].value

	for ii := uint(0); ii < end; ii++ {
		current := values[ii]

		if current == previous {
			values[ii] = byte(0)
			continue
		}

		// Find list index
		listIdx := int(this.buckets[int(current)&0xFF])

		p := this.heads[listIdx]
		idx := 0

		for i := 0; i < listIdx; i++ {
			idx += this.lengths[i]
		}

		// Find index in list (less than RESET_THRESHOLD iterations)
		for p.value != current {
			p = p.next
			idx++
		}

		values[ii] = byte(idx)

		// Unlink
		if p.previous != nil {
			p.previous.next = p.next
		}

		if p.next != nil {
			p.next.previous = p.previous
		}

		// Update head if needed
		if p == this.heads[listIdx] {
			this.heads[listIdx] = p.next
		}

		// Add to head of first list
		q := this.heads[0]
		q.previous = p
		p.next = q
		this.heads[0] = p

		// Update list information
		if listIdx != 0 {
			this.lengths[listIdx]--
			this.lengths[0]++
			this.buckets[int(current)&0xFF] = 0

			if this.lengths[0] > RESET_THRESHOLD || this.lengths[listIdx] == 0 {
				this.balanceLists(false)
			}
		}

		previous = current
	}

	return values
}
