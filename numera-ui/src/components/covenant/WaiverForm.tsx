'use client'

import { useEffect } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { zodResolver } from '@hookform/resolvers/zod'
import type { CovenantSignature } from '@/services/covenantApi'

const waiverSchema = z.object({
  waiverType: z.enum(['WAIVE', 'NOT_WAIVE']),
  durationType: z.enum(['INSTANCE', 'PERMANENT']),
  comments: z.string().max(2000).optional(),
  templateId: z.string().min(1, 'Template is required'),
  signatureId: z.string().min(1, 'Signature is required'),
  recipients: z.array(z.string().email('Invalid email address')).min(1, 'Select at least one recipient'),
})

export type WaiverFormValues = z.infer<typeof waiverSchema>

type WaiverFormProps = {
  defaultValues?: Partial<WaiverFormValues>
  templates: Array<{ id: string; name: string }>
  signatures: CovenantSignature[]
  contacts: Array<{ id: string; name: string; email: string }>
  onSubmit: (values: WaiverFormValues) => void
  showDecisionFields?: boolean
}

export default function WaiverForm({
  defaultValues,
  templates,
  signatures,
  contacts,
  onSubmit,
  showDecisionFields = true,
}: WaiverFormProps) {
  const {
    register,
    handleSubmit,
    setValue,
    watch,
    formState: { errors },
  } = useForm<WaiverFormValues>({
    resolver: zodResolver(waiverSchema),
    defaultValues: {
      waiverType: defaultValues?.waiverType ?? 'WAIVE',
      durationType: defaultValues?.durationType ?? 'INSTANCE',
      comments: defaultValues?.comments ?? '',
      templateId: defaultValues?.templateId ?? '',
      signatureId: defaultValues?.signatureId ?? '',
      recipients: defaultValues?.recipients ?? [],
    },
  })

  const selectedRecipients = watch('recipients')

  useEffect(() => {
    if (!defaultValues?.recipients) return
    setValue('recipients', defaultValues.recipients)
  }, [defaultValues?.recipients, setValue])

  const toggleRecipient = (email: string) => {
    const exists = selectedRecipients.includes(email)
    if (exists) {
      setValue('recipients', selectedRecipients.filter((item) => item !== email), { shouldValidate: true })
    } else {
      setValue('recipients', [...selectedRecipients, email], { shouldValidate: true })
    }
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} style={{ display: 'grid', gap: 14 }}>
      {showDecisionFields ? (
        <>
          <div className="grid-2" style={{ marginBottom: 0 }}>
            <div className="input-group">
              <label>Waiver Decision</label>
              <div style={{ display: 'flex', gap: 12 }}>
                <label style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 13 }}>
                  <input type="radio" value="WAIVE" {...register('waiverType')} /> Waive
                </label>
                <label style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 13 }}>
                  <input type="radio" value="NOT_WAIVE" {...register('waiverType')} /> Not Waive
                </label>
              </div>
            </div>
            <div className="input-group">
              <label>Duration</label>
              <div style={{ display: 'flex', gap: 12 }}>
                <label style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 13 }}>
                  <input type="radio" value="INSTANCE" {...register('durationType')} /> Instance
                </label>
                <label style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 13 }}>
                  <input type="radio" value="PERMANENT" {...register('durationType')} /> Permanent
                </label>
              </div>
            </div>
          </div>

          <div className="input-group">
            <label>Comments</label>
            <textarea className="input" rows={4} placeholder="Add waiver rationale and supporting details" {...register('comments')} />
            {errors.comments ? <span style={{ color: 'var(--danger)', fontSize: 12 }}>{errors.comments.message}</span> : null}
          </div>
        </>
      ) : null}

      <div className="grid-2" style={{ marginBottom: 0 }}>
        <div className="input-group">
          <label>Email Template</label>
          <select className="input" {...register('templateId')}>
            <option value="">Select template</option>
            {templates.map((template) => (
              <option key={template.id} value={template.id}>{template.name}</option>
            ))}
          </select>
          {errors.templateId ? <span style={{ color: 'var(--danger)', fontSize: 12 }}>{errors.templateId.message}</span> : null}
        </div>

        <div className="input-group">
          <label>Signature</label>
          <select className="input" {...register('signatureId')}>
            <option value="">Select signature</option>
            {signatures.map((signature) => (
              <option key={signature.id} value={signature.id}>{signature.name}</option>
            ))}
          </select>
          {errors.signatureId ? <span style={{ color: 'var(--danger)', fontSize: 12 }}>{errors.signatureId.message}</span> : null}
        </div>
      </div>

      <div className="input-group">
        <label>Recipients</label>
        <div className="card" style={{ padding: 10 }}>
          <div style={{ display: 'grid', gap: 8 }}>
            {contacts.map((contact) => {
              const checked = selectedRecipients.includes(contact.email)
              return (
                <label key={contact.id} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12 }}>
                  <span style={{ fontSize: 13 }}>
                    <span style={{ fontWeight: 600 }}>{contact.name}</span>
                    <span style={{ marginLeft: 8, color: 'var(--text-secondary)' }}>{contact.email}</span>
                  </span>
                  <input
                    type="checkbox"
                    checked={checked}
                    onChange={() => toggleRecipient(contact.email)}
                  />
                </label>
              )
            })}
          </div>
        </div>
        {errors.recipients ? <span style={{ color: 'var(--danger)', fontSize: 12 }}>{errors.recipients.message}</span> : null}
      </div>

      <button type="submit" className="btn btn-primary" style={{ justifySelf: 'end' }}>Continue</button>
    </form>
  )
}
